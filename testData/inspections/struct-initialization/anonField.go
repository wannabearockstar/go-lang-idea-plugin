package foo

type S struct {
	X string
	string
	Y int
}
func main() {
	var s S
	s = S{<caret><weak_warning descr="Unnamed field initialization">"X"</weak_warning>, <weak_warning descr="Unnamed field initialization">"a"</weak_warning>, Y: 1}
}
