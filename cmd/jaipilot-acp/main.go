package main

import (
	"github.com/JAIPilot/jaipilot/internal/jaipilot"
	"os"
)

func main() { os.Exit(jaipilot.Main(append([]string{"acp"}, os.Args[1:]...))) }
