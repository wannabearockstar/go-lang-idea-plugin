package foo

type S struct {
	t int
}

type B struct{
	s S
}

func main() {
	var  _  = []B{ s: {<weak_warning descr="Unnamed field initialization">1<caret></weak_warning>}}
}