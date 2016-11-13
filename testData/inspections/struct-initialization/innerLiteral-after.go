package foo

type S struct {
	r, t int
}

func main() {
	var _ = [][]S{
		{
			{r: 1, t: 1},
		},
	}
}