package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

type Response struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

func OK(c *gin.Context, data interface{}) {
	c.JSON(http.StatusOK, Response{Code: 0, Message: "ok", Data: data})
}

func Created(c *gin.Context, msg string, data interface{}) {
	c.JSON(http.StatusOK, Response{Code: 0, Message: msg, Data: data})
}

func Fail(c *gin.Context, httpStatus int, code int, msg string) {
	c.JSON(httpStatus, Response{Code: code, Message: msg})
}

func FailWithDetail(c *gin.Context, httpStatus int, code int, msg string, detail interface{}) {
	c.JSON(httpStatus, Response{Code: code, Message: msg, Data: detail})
}

func BadRequest(c *gin.Context, msg string) {
	Fail(c, http.StatusBadRequest, 10001, msg)
}

func Unauthorized(c *gin.Context, msg string) {
	Fail(c, http.StatusUnauthorized, 20001, msg)
}

func Forbidden(c *gin.Context, msg string) {
	Fail(c, http.StatusForbidden, 20002, msg)
}

func NotFound(c *gin.Context, msg string) {
	Fail(c, http.StatusNotFound, 30001, msg)
}

func Conflict(c *gin.Context, msg string) {
	Fail(c, http.StatusConflict, 30002, msg)
}

func InternalError(c *gin.Context, msg string) {
	Fail(c, http.StatusInternalServerError, 50001, msg)
}
