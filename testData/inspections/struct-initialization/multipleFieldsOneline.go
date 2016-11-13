package foo

type S struct {
	X, Y int
}
func main() {
	var s S
	s = S{<weak_warning descr="Unnamed field initialization">0</weak_warning>, <weak_warning descr="Unnamed field initialization">0</weak_warning>}
	s = S{X: 0, <weak_warning descr="Unnamed field initialization">0</weak_warning>}
	s = S{<weak_warning descr="Unnamed field initialization">0</weak_warning>, Y: 0}
}
