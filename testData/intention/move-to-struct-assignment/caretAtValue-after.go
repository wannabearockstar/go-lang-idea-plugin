package main

type S struct {
	foo string
}

func main() {
	var b
	s := S{foo: "bar"}
	b = "b"
	print(s.foo)
	print(b)
}