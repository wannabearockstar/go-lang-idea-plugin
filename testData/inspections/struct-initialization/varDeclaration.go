package foo

type S struct {
	X, Y int
}
func main() {
	var s S = S{<caret><weak_warning descr="Unnamed field initialization">0</weak_warning>, <weak_warning descr="Unnamed field initialization">0</weak_warning>}
}
