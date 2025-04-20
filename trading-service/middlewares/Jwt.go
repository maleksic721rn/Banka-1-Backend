package middlewares

import (
	"banka1.com/controllers/orders"
	jwtware "github.com/gofiber/contrib/jwt"
	"github.com/gofiber/fiber/v2"
	"github.com/golang-jwt/jwt/v5"
)

// JWTMiddleware returns a JWT middleware using JWKS for key retrieval
func JWTMiddleware(c *fiber.Ctx) error {
	return jwtware.New(jwtware.Config{
		Filter:         nil,
		SuccessHandler: jwtSuccessHandler,
		ErrorHandler:   jwtErrorHandler,
		JWKSetURLs:     []string{"https://idp.localhost/.well-known/o/oauth2/jwks"},
	})(c)
}

// jwtSuccessHandler handles successful JWT validation
func jwtSuccessHandler(c *fiber.Ctx) error {
	// Get the token from context
	token := c.Locals("user").(*jwt.Token)

	// Extract claims
	claims := token.Claims.(jwt.MapClaims)

	// Store claims in context for use in protected routes
	c.Locals("token", token.Raw)
	c.Locals("claims", claims)
	c.Locals("user_id", claims["id"])
	c.Locals("position", claims["position"])
	c.Locals("department", claims["department"])
	c.Locals("permissions", claims["permissions"])
	c.Locals("is_admin", claims["isAdmin"])
	c.Locals("is_employed", claims["isEmployed"])

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
	key, err := getSigningKey()
	if err != nil {
		return "", err
	}

	tokenString, err := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"uid":             uid,
		"buyerAccountId":  buyerAccountId,
		"sellerAccountId": sellerAccountId,
		"amount":          fmt.Sprintf("%f", amount),
	}).SignedString(key)

	if err != nil {
		return "", err
	}

	return tokenString, nil
}
