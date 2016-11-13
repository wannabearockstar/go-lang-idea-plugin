package foo

type S struct {
	r, t int
}

type B struct{
	S
}

func main() {
  var  _  = []B{  S: S{<weak_warning descr="Unnamed field initialization">2</weak_warning>, <weak_warning descr="Unnamed field initialization">3<caret></weak_warning>}}
}