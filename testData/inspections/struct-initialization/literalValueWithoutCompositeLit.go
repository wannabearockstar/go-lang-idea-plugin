package foo

type S struct {
	t int
}

type B struct{
	S
}

func main() {
	var  _  = []B{ S: {<weak_warning descr="Unnamed field initialization">1<caret></weak_warning>}}
}