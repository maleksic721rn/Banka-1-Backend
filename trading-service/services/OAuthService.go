package services

import (
	"banka1.com/oauth"
	"fmt"
	"sync"
)

// OAuthService manages OAuth functionality for the application
type OAuthService struct {
	client *oauth.Client
	once   sync.Once
}

var (
	oauthService     *OAuthService
	oauthServiceOnce sync.Once
)

// GetOAuthService returns a singleton instance of OAuthService
func GetOAuthService() *OAuthService {
	oauthServiceOnce.Do(func() {
		oauthService = &OAuthService{}
	})
	return oauthService
}

// Initialize sets up the OAuth client with the given configuration
func (s *OAuthService) Initialize(config oauth.ClientConfig) {
	s.once.Do(func() {
		s.client = oauth.NewOAuthClient(config)
	})
}

// GetClient returns the OAuth client
func (s *OAuthService) GetClient() *oauth.Client {
	return s.client
}

// GetAccessToken returns an access token for service-to-service communication
func (s *OAuthService) GetAccessToken() (string, error) {
	if s.client == nil {
		return "", fmt.Errorf("oauth client not initialized")
	}
	return s.client.GetAccessToken()
}

// GetAuthorizationHeader returns a formatted authorization header value
func (s *OAuthService) GetAuthorizationHeader() (string, error) {
	if s.client == nil {
		return "", fmt.Errorf("oauth client not initialized")
	}
	return s.client.GetAuthorizationHeader()
}
