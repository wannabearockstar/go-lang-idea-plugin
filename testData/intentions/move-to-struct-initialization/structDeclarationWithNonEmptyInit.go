package main

type S struct {
	foo string
	bar string
}

func main() {
	var s S = S{bar: "a"}
	s.foo <caret>= "bar"
	print(s.foo)
}