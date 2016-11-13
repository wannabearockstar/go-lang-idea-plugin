package foo

type S struct {
	X, Y int
}
func main() {
	s := S{<caret>Y: 1, 0}
}
