package services

import (
	"banka1.com/dto"
	"encoding/json"
	"fmt"
	"github.com/gofiber/fiber/v2"
	"io"
	"net/http"
	"os"
)

//const userServiceURL = "https://bank1.djues3.com/api/user/api/users/employees"

//const userServiceURL = "https://bank1.djues3.com/api/user/api/users/employees/filtered"

//func GetEmployees() ([]dto.EmployeeResponse, error) {
//	resp, err := http.Get(userServiceURL)
//
//	if err != nil {
//		return nil, err
//	}
//
//	defer resp.Body.Close()
//
//	if resp.StatusCode != http.StatusOK {
//		return nil, errors.New(fmt.Sprintf("Greska pri dohvatanju zaposlenih, status: %v", resp.StatusCode))
//	}
//
//	var employees []dto.EmployeeResponse
//	if err := json.NewDecoder(resp.Body).Decode(&employees); err != nil {
//		return nil, err
//	}
//
//	return employees, nil
//}

func GetEmployeesFiltered(c *fiber.Ctx, name, surname, email, position string) ([]dto.FilteredActuaryDTO, error) {

	tokenValue := c.Locals("token")
	tokenStr, ok := tokenValue.(string)
	if !ok || tokenStr == "" {
		return nil, fmt.Errorf("token nije pronađen u kontekstu")
	}

	basePath := os.Getenv("USER_SERVICE")
	if basePath == "" {
		return nil, fmt.Errorf("USER_SERVICE environment variable is not set")
	}

	uuserServiceURL := basePath + "/api/users/employees/actuaries/filtered"

	// Kreiranje URL-a sa query parametrima
	req, err := http.NewRequest("GET", uuserServiceURL, nil)
	if err != nil {
		fmt.Println("ERROR: Nece request ", err)

		return nil, err
	}

	// Dodavanje query parametara u URL
	q := req.URL.Query()
	if name != "" {
		q.Add("firstName", name)
	}
	if surname != "" {
		q.Add("lastName", surname)
	}
	if email != "" {
		q.Add("email", email)
	}
	if position != "" {
		q.Add("position", position)
	}
	req.URL.RawQuery = q.Encode()

	req.Header.Add("Authorization", "Bearer "+tokenStr)

	// Slanje GET zahteva
	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		fmt.Println("ERROR: Failed to send HTTP request:", err)
		return nil, err
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			fmt.Println("ERROR: Failed to close response body:", err)

		}
	}(resp.Body)

	if resp.StatusCode != http.StatusOK {
		fmt.Println("ERROR: Non-OK HTTP status:", resp.StatusCode)

		return nil, fmt.Errorf("Greska pri dohvatanju zaposlenih, status: %v", resp.StatusCode)
	}

	// Dekodiranje JSON odgovora
	var response dto.FilteredActuaryResponse

	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		fmt.Println("ERROR: Failed to decode response body:", err)
		return nil, err
	}

	employees := response.Data
	return employees, nil

}
