package foo

type S struct {
	X, Y int
}
func main() {
	s := S{<weak_warning descr="Unnamed field initialization"><caret>1</weak_warning>, <weak_warning descr="Unnamed field initialization">0</weak_warning>, 2}
}
