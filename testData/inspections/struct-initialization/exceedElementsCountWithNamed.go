package foo

type S struct {
	X, Y int
}
func main() {
	s := S{<caret>1, 0, X: 2}

}
