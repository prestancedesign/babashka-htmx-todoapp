(require '[org.httpkit.server :as srv]
         '[clojure.core.match :refer [match]]
         '[clojure.string :as str]
         '[hiccup.core :as h])

(defn template [body & {:keys [code] :or {code 200}}]
  {:status code
   :body
   (str
    "<!DOCTYPE html>"
    (h/html
     [:head
      [:meta {:charset "UTF-8"}]
      [:title "Htmx + Babashka"]
      [:link {:href "https://unpkg.com/todomvc-app-css@2.4.1/index.css" :rel "stylesheet"}]
      [:script {:src "https://unpkg.com/htmx.org@1.5.0/dist/htmx.min.js"}]
      [:script {:src "https://unpkg.com/hyperscript.org@0.8.1/dist/_hyperscript.min.js"}]]
     [:body
      [:section.todoapp
       [:header.header
        [:h1 "todos"]
        body]]]))})

(defn home-page []
  [:form {:hx-post "/todos"
          :hx-target "#todo-list"
          :hx-swap "afterbegin"
          :_ "on htmx:afterOnLoad set #txtTodo.value to ''"}
   [:input#txtTodo.new-todo {:name "todo"
                             :placeholder "What needs to be done?"
                             :autofocus ""}]])

(def not-found
  [:p "Error 404: Page not found"])

(defn routes [{:keys [request-method uri]}]
  (let [path (vec (rest (str/split uri #"/")))]
    (match [request-method path]
           [:get []] (template (home-page))
           :else (template not-found {:code 404}))))

(srv/run-server #'routes {:port 3000})
;; @(promise)
