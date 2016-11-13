package foo

import "io"

func _() {
	_ = io.LimitedReader{
		<caret><weak_warning descr="Unnamed field initialization">nil</weak_warning>,
	}
}