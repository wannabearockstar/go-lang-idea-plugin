package main

type S struct {
	foo string
	bar string
}

func main() {
	s := S{}
	s.foo, <caret>s.bar = "foo", "bar"
	print(s.foo)
}