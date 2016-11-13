package foo

type S struct {
	r, t int
}

func main() {
	var _ = map[string] []S{
		"s": {
			{r: 1, t: 1},
	},
}
}