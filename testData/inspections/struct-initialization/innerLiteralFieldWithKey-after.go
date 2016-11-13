package foo

type S struct {
	t int
}

type B struct{
	s S
}

func main() {
	var  _  = []B{ s: {s: 1}}
}