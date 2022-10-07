#!/usr/bin/env bb

(require '[babashka.deps :as deps])
(deps/add-deps '{:deps {org.clojars.askonomm/ruuter {:mvn/version "1.3.2"}}})

(require '[org.httpkit.server :as srv]
         '[clojure.java.browse :as browse]
         '[ruuter.core :as ruuter]
         '[clojure.pprint :refer [cl-format]]
         '[clojure.string :as str]
         '[hiccup.core :as h])

(import '[java.net URLDecoder])

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Config
;;;;;;;;;;;;;;;;;;;;;;;;;;

(def port 3000)

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mimic DB (in-memory)
;;;;;;;;;;;;;;;;;;;;;;;;;;

(def todos (atom (sorted-map 1 {:id 1 :name "Taste htmx with Babashka" :done true}
                             2 {:id 2 :name "Buy a unicorn" :done false})))

(def todos-id (atom (count @todos)))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; "DB" queries
;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-todo! [name]
  (let [id (swap! todos-id inc)]
    (swap! todos assoc id {:id id :name name :done false})))

(defn update-todo! [id name]
  (swap! todos assoc-in [(Integer. id) :name] name))

(defn toggle-todo! [id]
  (swap! todos update-in [(Integer. id) :done] not))

(defn remove-todo! [id]
  (swap! todos dissoc (Integer. id)))

(defn filtered-todo [filter-name todos]
  (case filter-name
    "active" (remove #(:done (val %)) todos)
    "completed" (filter #(:done (val %)) todos)
    "all" todos
    todos))

(defn get-items-left []
  (count (remove #(:done (val %)) @todos)))

(defn todos-completed []
  (count (filter #(:done (val %)) @todos)))

(defn remove-all-completed-todo []
  (reset! todos (into {} (remove #(:done (val %)) @todos))))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Template and components
;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn todo-list [todos]
  (for [todo todos]
    (todo-item (val todo))))

(defn todo-edit [id name]
  [:form {:hx-patch (str "/todos/update/" id)}
   [:input.edit {:type "text"
                 :name "name"
                 :value name}]])

(defn item-count []
  (let [items-left (get-items-left)]
    [:span#todo-count.todo-count {:hx-swap-oob "true"}
     [:strong items-left] (cl-format nil " item~p " items-left) "left"]))

(defn todo-filters [filter]
  [:ul#filters.filters {:hx-swap-oob "true"}
   [:li [:a {:hx-get "/?filter=all"
             :hx-push-url "true"
             :hx-target "#todo-list"
             :class (when (= filter "all") "selected")} "All"]]
   [:li [:a {:hx-get "/?filter=active"
             :hx-push-url "true"
             :hx-target "#todo-list"
             :class (when (= filter "active") "selected")} "Active"]]
   [:li [:a {:hx-get "/?filter=completed"
             :hx-push-url "true"
             :hx-target "#todo-list"
             :class (when (= filter "completed") "selected")} "Completed"]]])

(defn clear-completed-button []
  [:button#clear-completed.clear-completed
   {:hx-delete "/todos"
    :hx-target "#todo-list"
    :hx-swap-oob "true"
    :hx-push-url "/"
    :class (when-not (pos? (todos-completed)) "hidden")}
   "Clear completed"])

(defn template [filter]
  (list
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
       [:form
        {:hx-post "/todos"
         :hx-target "#todo-list"
         :hx-swap "beforeend"
         :_ "on htmx:afterOnLoad set #txtTodo.value to ''"}
        [:input#txtTodo.new-todo
         {:name "todo"
          :placeholder "What needs to be done?"
          :autofocus ""}]]]
      [:section.main
       [:input#toggle-all.toggle-all {:type "checkbox"}]
       [:label {:for "toggle-all"} "Mark all as complete"]]
      [:ul#todo-list.todo-list
       (todo-list (filtered-todo filter @todos))]
      [:footer.footer
       (item-count)
       (todo-filters filter)
       (clear-completed-button)]]
     [:footer.info
      [:p "Click to edit a todo"]
      [:p "Created by "
       [:a {:href "https://twitter.com/PrestanceDesign"} "Michaël Sλlihi"]]
      [:p "Part of "
       [:a {:href "http://todomvc.com"} "TodoMVC"]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-body [body]
  (-> body
      slurp
      (str/split #"=")
      second
      URLDecoder/decode))

(defn parse-query-string [query-string]
  (when query-string
    (-> query-string
        (str/split #"=")
        second)))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render [handler & [status]]
  {:status (or status 200)
   :body (h/html handler)})

(defn app-index [{:keys [query-string headers]}]
  (let [filter (parse-query-string query-string)
        ajax-request? (get headers "hx-request")]
    (if (and filter ajax-request?)
      (render (list (todo-list (filtered-todo filter @todos))
                    (todo-filters filter)))
      (render (template filter)))))

(defn add-item [{body :body}]
  (let [name (parse-body body)
        todo (add-todo! name)]
    (render (list (todo-item (val (last todo)))
                  (item-count)))))

(defn edit-item [{{id :id} :params}]
  (let [{:keys [id name]} (get @todos (Integer. id))]
    (render (todo-edit id name))))

(defn update-item [{{id :id} :params body :body}]
  (let [name (parse-body body)
        todo (update-todo! id name)]
    (render (todo-item (get todo (Integer. id))))))

(defn patch-item [{{id :id} :params}]
  (let [todo (toggle-todo! id)]
    (render (list (todo-item (get todo (Integer. id)))
                  (item-count)
                  (clear-completed-button)))))

(defn delete-item [{{id :id} :params}]
  (remove-todo! id)
  (render (item-count)))

(defn clear-completed [_]
  (remove-all-completed-todo)
  (render (list (todo-list @todos)
                (item-count)
                (clear-completed-button))))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routes
;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes [{:path     "/"
              :method   :get
              :response app-index}
             {:path     "/todos/edit/:id"
              :method   :get
              :response edit-item}
             {:path     "/todos"
              :method   :post
              :response add-item}
             {:path     "/todos/update/:id"
              :method   :patch
              :response update-item}
             {:path     "/todos/:id"
              :method   :patch
              :response patch-item}
             {:path     "/todos/:id"
              :method   :delete
              :response delete-item}
             {:path     "/todos"
              :method   :delete
              :response clear-completed}])

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server
;;;;;;;;;;;;;;;;;;;;;;;;;;

(when (= *file* (System/getProperty "babashka.file"))
  (let [url (str "http://localhost:" port "/")]
    (srv/run-server #(ruuter/route routes %) {:port port})
    (println "serving" url)
    (browse/browse-url url)
    @(promise)))
