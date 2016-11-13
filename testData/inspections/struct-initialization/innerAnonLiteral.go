package foo

type S struct {
	t int
}

func main() {
	var  _  = []S{ {<weak_warning descr="Unnamed field initialization"><caret>1</weak_warning>} }
}
