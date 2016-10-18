package main

type S struct {
	foo string
	bar string
}


func main() {
	s, str := S{}, "bar"
	s.foo, s.str<caret> = "foo", bar
	print(s.foo)
}