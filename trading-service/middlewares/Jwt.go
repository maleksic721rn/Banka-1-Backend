package middlewares

import (
	"encoding/base64"
	"fmt"
	jwtware "github.com/gofiber/contrib/jwt"
	"github.com/gofiber/fiber/v2"
	"github.com/golang-jwt/jwt/v5"
	"os"
)

// JWTMiddleware returns a JWT middleware using either JWKS or local key for validation
func JWTMiddleware(c *fiber.Ctx) error {
	// Check if we're in test mode (using local key)
	if os.Getenv("JWT_TEST_MODE") == "true" {
		return jwtware.New(jwtware.Config{
			SigningKey:     getSigningKeyOrPanic(),
			SuccessHandler: jwtSuccessHandler,
			ErrorHandler:   jwtErrorHandler,
		})(c)
	}

	// Production mode (using JWKS)
	return jwtware.New(jwtware.Config{
		Filter:         nil,
		SuccessHandler: jwtSuccessHandler,
		ErrorHandler:   jwtErrorHandler,
		JWKSetURLs:     []string{"https://idp.localhost/o/oauth2/jwks"},
	})(c)
}

// jwtSuccessHandler handles successful JWT validation
func jwtSuccessHandler(c *fiber.Ctx) error {
	// Get the token from context
	token := c.Locals("user").(*jwt.Token)

	// Extract claims
	claims := token.Claims.(jwt.MapClaims)
	resourceAccess := claims["resource_access"].(map[string]interface{})
	// Store claims in context for use in protected routes
	c.Locals("token", token.Raw)
	c.Locals("claims", claims)
	c.Locals("user_id", resourceAccess["id"])
	c.Locals("position", resourceAccess["position"])
	c.Locals("department", resourceAccess["department"])
	c.Locals("permissions", resourceAccess["permissions"])
	c.Locals("is_admin", resourceAccess["isAdmin"])
	c.Locals("is_employed", resourceAccess["isEmployed"])

	return c.Next()
}

// jwtErrorHandler handles JWT validation errors
func jwtErrorHandler(c *fiber.Ctx, err error) error {
	return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
		"error":   "Unauthorized - " + err.Error(),
		"success": false,
	})
}

func GenerateToken(req interface{}) (*interface{}, *interface{}) {
	return nil, nil
}

func NewOrderTokenDirect(uid string, buyerAccountId uint, sellerAccountId uint, amount float64) (string, error) {
	return "", nil
}

// Testing utilities

// getSigningKeyOrPanic retrieves the JWT signing key or panics
func getSigningKeyOrPanic() jwtware.SigningKey {
	key, err := getSigningKey()
	if err != nil {
		panic(err)
	}
	return jwtware.SigningKey{Key: key, JWTAlg: "HS256"}
}

// getSigningKey retrieves the JWT signing key from the environment
func getSigningKey() ([]byte, error) {
	encodedSecret := os.Getenv("JWT_SECRET")
	if encodedSecret == "" {
		return nil, fmt.Errorf("JWT_SECRET environment variable not set")
	}

	decodedSecret, err := base64.StdEncoding.DecodeString(encodedSecret)
	if err != nil {
		return nil, fmt.Errorf("failed to decode JWT_SECRET: %w", err)
	}

	return decodedSecret, nil
}
