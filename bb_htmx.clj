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
      [:script {:src "https://unpkg.com/htmx.org@1.5.0/dist/htmx.min.js"}]
      [:script {:src "https://unpkg.com/hyperscript.org@0.8.1/dist/_hyperscript.min.js"}]]
     [:body
      body]))})

(def home-page
  [:div.container
   [:button {:type "button"
             :_ "on click decrement #txtCount.value"} "-"]
   [:input#txtCount {:type "text" :value 0}]
   [:button {:type "button"
             :_ "on click increment #txtCount.value"} "+"]
   [:label "What is your name?"]
   [:input {:type "text" :name "my-name" :id "my-name"
            :_ "on keyup set #hello.innerText to #my-name.value"}]
   [:h1 "Hello " [:span#hello "World!"]]])

(def users-page
  [:h1 "Users list"])

(defn user-page [id]
  [:h1 "User id is: " id])

(def not-found
  [:p "Error 404: Page not found"])

(defn routes [{:keys [request-method uri]}]
  (let [path (vec (rest (str/split uri #"/")))]
    (match [request-method path]
           [:get []] (template home-page)
           [:get ["users"]] (template users-page)
           [:get ["user" id]] (template (user-page id))
           :else (template not-found {:code 404}))))

(srv/run-server #'routes {:port 3000})
@(promise)
