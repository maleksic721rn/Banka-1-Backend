package middlewares

import (
	"fmt"
	"github.com/gofiber/fiber/v2/log"
	"os"


	"github.com/gofiber/fiber/v2"
)

func Auth(c *fiber.Ctx) error {
	return JWTMiddleware(c)
}

func DepartmentCheck(requiredDept string) fiber.Handler {
	return func(c *fiber.Ctx) error {
		// Dohvatanje department vrednosti iz Locals
		department, ok := c.Locals("department").(string)
		if !ok || department != requiredDept {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"success": false,
				"error":   "Unauthorized: Invalid department",
			})
		}

		// Nastavi sa sledećim middleware-om ili handler-om
		return c.Next()
	}
}

func RequirePermission(requiredPermission string) fiber.Handler {
	return func(c *fiber.Ctx) error {
		permissions, ok := c.Locals("permissions").([]interface{})
		if !ok {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
				"success": false,
				"error":   "Unauthorized: No permissions found",
			})
		}
		log.Infof("Permissions found: %v", permissions)

		for _, perm := range permissions {
			log.Infof("Checking permission: %v", perm)
			if strPerm, ok := perm.(string); ok && strPerm == requiredPermission {
				log.Infof("Permission '%s' granted", requiredPermission)
				return c.Next()
			}
		}
		log.Infof("Permission '%s' denied", requiredPermission)
		return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
			"success": false,
			"error":   fmt.Sprintf("Unauthorized: Missing permission '%s'", requiredPermission),
		})
	}
}

func RequireInterbankApiKey(c *fiber.Ctx) error {
	key := c.Get("X-Api-Key")
	expected := os.Getenv("BANK1_SECURITY")
	if key == "" || key != expected {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
			"success": false,
			"error":   "Unauthorized: invalid interbank API key",
		})
	}
	return c.Next()
}
