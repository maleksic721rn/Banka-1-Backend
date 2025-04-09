package controllers

import (
	"banka1.com/db"
	"banka1.com/types"
	"github.com/gofiber/fiber/v2"
	"log"
)

func GetAvailableSecurities(c *fiber.Ctx) error {
	var securities []types.Security

	if err := db.DB.Find(&securities).Error; err != nil {
		log.Printf("[ERROR] Failed to fetch available securities: %v\n", err)
		return c.Status(500).JSON(types.Response{
			Success: false,
			Error:   "Failed to fetch available securities.",
		})
	}

	log.Printf("[INFO] Fetched %d available securities.\n", len(securities))
	return c.JSON(types.Response{
		Success: true,
		Data:    securities,
	})
}
