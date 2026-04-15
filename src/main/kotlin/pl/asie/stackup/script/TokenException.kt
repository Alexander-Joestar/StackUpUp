package pl.asie.stackup.script

class TokenException : Exception {
    constructor(error: String) : super(error)
    constructor(error: String, parent: Exception) : super(error, parent)
}
