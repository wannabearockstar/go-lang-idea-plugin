package foo

type S struct {
	t int
}

type B struct{
	s S
}

func main() {
	var  _  = []B{ B{<weak_warning descr="Unnamed field initialization">S{t: 1}<caret></weak_warning>} }
}