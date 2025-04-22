package shared

import (
	"crypto/tls"
	"github.com/gofiber/fiber/v2"
	"log"
	"net/http"
	"os"
	"strings"
)

func HttpClient() *http.Client {
	ignoreSSL := os.Getenv("IGNORE_SSL_CERTS")

	if ignoreSSL == "" || strings.ToLower(ignoreSSL) == "true" {
		log.Println("Warning: SSL certificate verification disabled")
		tr := &http.Transport{
			TLSClientConfig: &tls.Config{
				InsecureSkipVerify: true,
			},
		}
		return &http.Client{Transport: tr}
	}

	return &http.Client{}
}

func FiberAgent() *fiber.Agent {
	agent := fiber.AcquireAgent()

	ignoreSSL := os.Getenv("IGNORE_SSL_CERTS")
	if ignoreSSL == "" || strings.ToLower(ignoreSSL) == "true" {
		log.Println("Warning: SSL certificate verification disabled for Fiber Agent")
		agent.InsecureSkipVerify()
	}

	return agent
}
