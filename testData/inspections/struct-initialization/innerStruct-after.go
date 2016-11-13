package foo

func main() {
	type B struct {
		Y int
	}

	type S struct {
		X int
		b B
		Z int
	}

	s := S{X: 1, b: B{Y: 2}}
	print(s.b.Y)
}