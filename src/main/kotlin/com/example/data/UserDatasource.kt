package com.example.data

data class User(
    val email: String,
    val password: String,
    val id: Int
)

class UserDatasource {
    private val users = mutableListOf(
        User(email = "asdf@asdf.com", password = "password", 1),
        User(email = "test@test.com", password = "test", 2),
    )

    fun getUserByEmail(email: String): User? {
        users.forEach {
            if (it.email == email) return it
        }
        return null
    }

    fun insertUser(user: User) {
        users.add(user)
    }

    fun getAllUsers(): List<User> {
        return users
    }
}