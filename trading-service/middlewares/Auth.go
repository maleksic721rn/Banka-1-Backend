package middlewares

import (
	"fmt"

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

		for _, perm := range permissions {
			if strPerm, ok := perm.(string); ok && strPerm == requiredPermission {
				return c.Next()
			}
		}

		return c.Status(fiber.StatusForbidden).JSON(fiber.Map{
			"success": false,
			"error":   fmt.Sprintf("Unauthorized: Missing permission '%s'", requiredPermission),
		})
	}
}
