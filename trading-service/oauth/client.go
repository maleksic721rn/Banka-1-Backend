package oauth

import (
	"context"
	"fmt"
	"sync"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/clientcredentials"
)

// ClientConfig holds configuration for OAuth2 client credentials flow
type ClientConfig struct {
	TokenURL     string
	ClientID     string
	ClientSecret string
	Scopes       []string
}

// Client handles token acquisition and caching
type Client struct {
	config       *clientcredentials.Config
	currentToken *oauth2.Token
	tokenMu      sync.RWMutex
	// Optional: add a context for cancellation
}

// NewOAuthClient creates a new OAuth client with the given configuration
func NewOAuthClient(config ClientConfig) *Client {
	ccConfig := &clientcredentials.Config{
		ClientID:     config.ClientID,
		ClientSecret: config.ClientSecret,
		TokenURL:     config.TokenURL,
		Scopes:       config.Scopes,
		AuthStyle:    oauth2.AuthStyleInHeader,
	}

	return &Client{
		config: ccConfig,
	}
}

// GetToken returns a valid OAuth token, fetching a new one if necessary
// Uses the client credentials flow
func (c *Client) GetToken() (*oauth2.Token, error) {
	c.tokenMu.RLock()
	token := c.currentToken
	c.tokenMu.RUnlock()

	// Check if we have a valid token
	if token != nil && token.Valid() {
		return token, nil
	}

	// Need to get a new token
	return c.refreshToken()
}

// refreshToken fetches a new access token
func (c *Client) refreshToken() (*oauth2.Token, error) {
	c.tokenMu.Lock()
	defer c.tokenMu.Unlock()

	if c.currentToken != nil && c.currentToken.Valid() {
		return c.currentToken, nil
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	token, err := c.config.Token(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get token: %w", err)
	}

	c.currentToken = token
	return token, nil
}

// GetAccessToken returns just the access token string
func (c *Client) GetAccessToken() (string, error) {
	token, err := c.GetToken()
	if err != nil {
		return "", err
	}
	return token.AccessToken, nil
}

// GetAuthorizationHeader returns the full "Bearer token" for use in the Authorization header
func (c *Client) GetAuthorizationHeader() (string, error) {
	token, err := c.GetAccessToken()
	if err != nil {
		return "", err
	}
	return "Bearer " + token, nil
}
