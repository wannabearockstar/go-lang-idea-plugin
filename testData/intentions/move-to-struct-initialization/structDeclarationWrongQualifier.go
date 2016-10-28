package main

type S struct {
	foo string
}

func main() {
	var s S
	var b S
	s.foo <caret>= "bar"
	print(b.foo)
}