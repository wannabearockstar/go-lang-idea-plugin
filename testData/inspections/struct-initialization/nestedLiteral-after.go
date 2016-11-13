package foo

type S struct {
	r, t int
}

type B struct{
	S
}

func main() {
  var  _  = []B{  S: S{r: 2, t: 3}}
}