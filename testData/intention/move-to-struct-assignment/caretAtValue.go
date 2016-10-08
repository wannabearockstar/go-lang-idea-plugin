package main

type S struct {
	foo string
}

func main() {
	var b
	s := S{}
	s.foo, b = <caret>"bar", "b"
	print(s.foo)
	print(b)
}