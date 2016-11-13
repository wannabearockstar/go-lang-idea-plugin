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

	s := S{X: 1, B: B{Y: 2}, Z: 3}
	print(s.B.Y)
}