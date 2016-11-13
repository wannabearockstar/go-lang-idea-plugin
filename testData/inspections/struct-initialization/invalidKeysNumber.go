package main

type S struct {
	r, t int
}

type B struct{
	S
}
func main() {
	var  _  = []B{  S: {S: 2,  3}}
}
