package main

type S struct {
	foo string
	bar string
}

func main() {
	var s S = S{bar: "a", foo: "bar"}

	print(s.foo)
}