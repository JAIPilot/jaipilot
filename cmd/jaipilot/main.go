package main

import (
	"github.com/JAIPilot/jaipilot/internal/jaipilot"
	"os"
)

func main() { os.Exit(jaipilot.Main(os.Args[1:])) }
