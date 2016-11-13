package foo

type S struct {
	t int
}

type B struct{
	s S
}

func main() {
	var  _  = []B{ B{s: S{t: 1}} }
}