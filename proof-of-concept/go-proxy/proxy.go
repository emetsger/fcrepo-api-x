package main

import (
	"bufio"
	"bytes"
	"flag"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strings"
	"fmt"
)

var newLine = []byte{'\n'}

var rewriteTypes = map[string]struct{}{
	"text/turtle": {},
	"text/html":   {},
}

type redactingTransport struct {
	delegate http.RoundTripper
	linkHeader string
	contextUri string
}


type writerWrapper struct {
	writer  http.ResponseWriter
	buffer  *bytes.Buffer
	scanner *bufio.Scanner
	extern  []byte
	intern  []byte
}

// Right now, this is a really simple proxy that uses defaults
// nearly everywhere, and does nothing other than blindly proxy a
// request to a single server.
func main() {
	var (
		bind, bindPath, proxyHost, proxyPath, proxyScheme string
		intern, extern                       		  []byte
		mux                                               *http.ServeMux
		provided, rewriteHostHeader                       bool
		err                                               error
		jsonLdContext                                     string
	)

	// Start off by parsing args.  cmdline > env > defaults

	bind, provided = os.LookupEnv("BIND_ADDR")
	if !provided {
		bind = ":8090"
	}
	flag.StringVar(&bind, "ba", bind, "Bind address [ip:port]; 0.0.0.0 to listen on all IPs")

	bindPath, provided = os.LookupEnv("BIND_PATH")
	if !provided {
		bindPath = "/"
	}
	flag.StringVar(&bindPath, "bp", bindPath, "Bind path; incoming requests to this path will be proxied")

	proxyHost, provided = os.LookupEnv("PROXY_ADDR")
	if !provided {
		proxyHost = "127.0.0.1:8080"
	}
	flag.StringVar(&proxyHost, "ph", proxyHost, "Proxy host; requests will be proxied to this host")

	proxyPath, provided = os.LookupEnv("PROXY_PATH")
	if !provided {
		proxyPath = "/"
	}
	flag.StringVar(&proxyPath, "pp", proxyPath, "Proxy Path; requests will be proxied to this path")

	proxyScheme, provided = os.LookupEnv("PROXY_SCHEME")
	if !provided {
		proxyScheme = "http"
	}
	flag.StringVar(&proxyScheme, "ps", proxyScheme, "Proxy scheme; requests will be proxied using this scheme")

	flag.BoolVar(&rewriteHostHeader, "r", false,
		"Re-write Host header")

	jsonLdContext, provided = os.LookupEnv("PROXY_LDCONTEXT")
	if !provided {
		jsonLdContext = "";
	}
	flag.StringVar(&jsonLdContext, "ctx", jsonLdContext,
		"JSON-LD context location; added as Link header on application/json responses")

	flag.Parse()

	log.Println("Listening to", bind, bindPath)
	log.Println("Proxying to", proxyHost, proxyPath)

	// create our proxy
	proxy := httputil.NewSingleHostReverseProxy(&url.URL{Host: proxyHost, Path: proxyPath, Scheme: proxyScheme})

	proxy.Director = func(req *http.Request) {
		// request path has already been re-written to the proxied version of the path
		log.Printf("Director received request: %v", req)
		req.URL = &url.URL{Host: proxyHost, Path: req.URL.Path, Scheme: proxyScheme}
		if rewriteHostHeader {
			req.Host = proxyHost
		}
	}

	proxy.Transport = &redactingTransport{
		delegate: http.DefaultTransport,
		contextUri: jsonLdContext,
		linkHeader: "<%s>; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json\"",
	}

	// re-write request path
	rewriteURL := func(to http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			log.Printf("Re-writing URL path: '%s' in '%s' with '%s'", bindPath, r.URL.Path, proxyPath)
			r.URL.Path = strings.Replace(r.URL.Path, bindPath, proxyPath, 1)
			log.Printf("Re-written URL path: '%s'", r.URL.Path)
			to.ServeHTTP(w, r)
		})
	}

	// These are the baseURIs we'll be substituting if we rewrite the body
	intern = []byte(bind + proxyPath)
	extern = []byte(bind + bindPath)

	// rewrite the response body
	rewriteBody := func(to http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			to.ServeHTTP(newWriterWrapper(w, intern, extern), r)
		})
	}

	mux = http.NewServeMux()
	mux.Handle(bindPath, rewriteBody(rewriteURL(proxy)))

	if err = http.ListenAndServe(bind, mux); err != nil {
		log.Panic("Could not start proxy", err)
	}
}

func newWriterWrapper(w http.ResponseWriter, intern []byte, extern []byte) *writerWrapper {
	buf := new(bytes.Buffer)
	return &writerWrapper{writer: w, buffer: buf, scanner: bufio.NewScanner(buf), intern: intern, extern: extern}
}

func (w *writerWrapper) Header() http.Header {
	return w.writer.Header()
}

func (w *writerWrapper) WriteHeader(code int) {
	w.writer.WriteHeader(code)
}

func (t *redactingTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	log.Printf("Request url: %s", req.URL)
	log.Printf("Request: %v\n", req)

	resp, err := t.delegate.RoundTrip(req)
	if err != nil {
		log.Panicf("Response error: %s", err)
	}
	if resp == nil {
		log.Panic("Response is nil")
	} else if resp.StatusCode > 299 {
		log.Printf("Unexpected response: %d, %s\n", resp.StatusCode, resp.Status)
	} else {
		if (t.contextUri != "") {
			resp.Header.Add("Link", fmt.Sprintf(t.linkHeader, t.contextUri))
		}
		resp.Header.Del("Content-Length")
		resp.Header.Add("Access-Control-Expose-Headers", "Link")
	
		log.Printf("Response: %v\n", resp)
	}

	return resp, err
}

// Capture the content written to the response; for each line
// perform a substitution.
func (w *writerWrapper) Write(content []byte) (int, error) {

	if _, ok := rewriteTypes[w.Header().Get("Content-Type")]; ok {

		_, err := w.buffer.Write(content)
		if err != nil {
			log.Panic("Buffer write failed!", err)
		}

		var written int

		for w.scanner.Scan() {
			i, err := w.writer.Write(bytes.Replace(w.scanner.Bytes(), w.intern, w.extern, -1))
			if err != nil {
				log.Panic("Write to http writer failed!", err)
			}

			written += i
			i, err = w.writer.Write(newLine)
			written += i
		}

		if w.scanner.Err() != nil {
			log.Panic("Error scanning text", w.scanner.Err())
		}

		return written, err
	}

	return w.writer.Write(content)

}
