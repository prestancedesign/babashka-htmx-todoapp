# Babashka htmx todo app
Quick example of a todo list application using [Babashka](https://github.com/babashka/babashka) and [htmx](https://htmx.org/).

With htmx get a single page app without writing a single line of Javascript.

From their own web page:
> htmx allows you to access AJAX, CSS Transitions, WebSockets and Server Sent Events directly in HTML, using attributes, so you can build modern user interfaces with the simplicity and power of hypertext

> htmx is small (~10k min.gz'd), dependency-free, extendable & IE11 compatible

![Babashka Htmx](https://github.com/PrestanceDesign/babashka-htmx-todoapp/blob/master/cover.png)

## Run the application

Clone this repo and run with Babashka:

    git clone https://github.com/prestancedesign/babashka-htmx-todoapp
    cd babashka-htmx-todoapp
    bb htmx_todoapp.clj

Your browser will prompt automatically to the url http://localhost:3000
