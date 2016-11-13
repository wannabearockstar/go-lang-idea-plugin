package foo

type S struct {
	t int
}

type P struct {
	r int
}

type B struct{
	s S
	p P
}

func main() {
	var  _  = []B{{ p: {r: 1}}}
}