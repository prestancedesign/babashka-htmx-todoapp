(require '[org.httpkit.server :as srv]
         '[clojure.core.match :refer [match]]
         '[clojure.string :as str]
         '[hiccup.core :as h])

(import '[java.net URLDecoder])

(def todos (atom [{:id 1 :name "Taste htmx" :done true}
                  {:id 2 :name "Buy a unicorn" :done false}]))

(defn find-todo [id todos-list]
  (first (filter #(= (Integer. id) (:id %)) todos-list)))

(defn todo-item [{:keys [id name done]}]
  [:li {:id (str "todo-" id)
        :class (when done "completed")}
   [:div.view
    [:input.toggle {:hx-patch (str "/todos/" id)
                    :type "checkbox"
                    :checked done
                    :hx-target (str "#todo-" id)
                    :hx-swap "outerHTML"}]
    [:label {:hx-get (str "/todos/edit/" id)
             :hx-target (str "#todo-" id)
             :hx-swap "outerHTML"} name]
    [:button.destroy {:hx-delete (str "/todos/" id)
                      :_ (str "on htmx:afterOnLoad remove #todo-" id)}]]])

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
      [:script {:src "https://unpkg.com/htmx.org@1.5.0/dist/htmx.min.js" :defer true}]
      [:script {:src "https://unpkg.com/hyperscript.org@0.8.1/dist/_hyperscript.min.js" :defer true}]]
     [:body
      [:section.todoapp
       [:header.header
        [:h1 "todos"]
        body]
       [:section.main
        [:input#toggle-all.toggle-all {:type "checkbox"}]
        [:label {:for "toggle-all"} "Mark all as complete"]]
       [:ul#todo-list.todo-list
        (for [todo @todos]
          (todo-item todo))]]]))})

(defn home-page []
  [:form {:hx-post "/todos"
          :hx-target "#todo-list"
          :hx-swap "afterbegin"
          :_ "on htmx:afterOnLoad set #txtTodo.value to ''"}
   [:input#txtTodo.new-todo {:name "todo"
                             :placeholder "What needs to be done?"
                             :autofocus ""}]])

(defn edit-item [id]
  (let [{:keys [id name]} (find-todo id @todos)]
    (h/html
     [:form {:hx-post (str"/todos/update/" id)}
      [:input.edit {:type "text"
                    :name "name"
                    :value name}]])))

(defn update-item [req id]
  (let [todo (find-todo id @todos)
        name (-> req
                 :body
                 slurp
                 (str/split #"=")
                 second
                 URLDecoder/decode)]
    (swap! todos assoc-in [(dec (Integer. id)) :name] name)
    (h/html (todo-item (assoc todo :name name)))))

(defn patch-item [id]
  (let [todo (find-todo id @todos)]
    (swap! todos update-in [(dec (Integer. id)) :done] not)
    (h/html (todo-item (update todo :done not)))))

(defn delete-item [id]
  (swap! todos (fn [x] (remove #(= (:id %) (Integer. id)) x))))

(def not-found
  [:p "Error 404: Page not found"])

(defn routes [{:keys [request-method uri] :as req}]
  (let [path (vec (rest (str/split uri #"/")))]
    (match [request-method path]
           [:get []] (template (home-page))
           [:get ["todos" "edit" id]] {:body (edit-item id)}
           [:post ["todos" "update" id]] {:body (update-item req id)}
           [:patch ["todos" id]] {:body (patch-item id)}
           [:delete ["todos" id]] {:body (delete-item id)}
           :else (template not-found {:code 404}))))

(srv/run-server #'routes {:port 3000})
;; @(promise)
