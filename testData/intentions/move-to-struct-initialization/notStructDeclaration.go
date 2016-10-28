package main

type S struct {
	foo string
}

func main() {
	var s string
	s.foo <caret>= "bar"
	print(s.foo)
}