package foo

type S struct {
	t int
}

type B struct{
	S
}

func main() {
	var  _  = []B{ S: {S: 1}}
}