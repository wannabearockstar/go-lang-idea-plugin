package foo

func main() {
	type B struct {
		Y int
	}

	type S struct {
		X int
		B
		Z int
	}

	s := S{<weak_warning descr="Unnamed field initialization">1<caret></weak_warning>, <weak_warning descr="Unnamed field initialization">B{Y: 2}</weak_warning>, <weak_warning descr="Unnamed field initialization">3</weak_warning>}
	print(s.B.Y)
}