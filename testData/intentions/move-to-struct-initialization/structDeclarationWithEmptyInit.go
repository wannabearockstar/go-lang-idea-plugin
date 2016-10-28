package main

type S struct {
	foo string
}

func main() {
	var s S = S{}
	s.foo <caret>= "bar"
	print(s.foo)
}