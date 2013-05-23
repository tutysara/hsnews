(ns hsnews.views.common
  (:use noir.core
        [hiccup.page-helpers :only [include-css html5 link-to]]
        somnium.congomongo)
  (:require [clojure.string :as string]
            [noir.response :as resp]
            [noir.request :as req]
            [hsnews.models.user :as users]
            [hsnews.models.post :as posts]
            [hsnews.models.comment :as comments]
            [noir.session :as session]
            [hsnews.utils :as utils]))

(defn get-request-uri []
  (:uri (req/ring-request)))

(defn get-referer []
  ((:headers (req/ring-request)) "referer"))

(pre-route "/*" {:keys [uri]}
           (when-not (or
                       (users/current-user)
                       (= uri "/login")
                       (= uri "/sessions/create")
                       (= uri "/register") ;; uncomment to allow registration
                       (= uri "/users/create") ;; uncomment to allow registration
                       (re-find #"^/(css)|(img)|(js)|(favicon)" uri))
            (session/flash-put! (get-request-uri))
            (resp/redirect "/login")))

(defn extract-domain-from-url [url]
  (second (re-find #"^(?:[^:/]*://)?(?:www\.)?([^/\?]+)(?:.*)$" url)))

(defn format-points [points]
  (str points " point" (if (not= points 1) "s")))

(defn comment-points [{:keys [points] :as com}]
  (if (comments/is-author? com) (format-points points)))

(defpartial user-link [hs_id]
  (link-to {:class "userLink"} (str "/users/" hs_id) (users/get-username hs_id)))

(defpartial comment-link [c_id text]
  (link-to {:class "userLink"} (str "/comments/" c_id) text))

(defpartial upvote-comment-link [com]
  (if (comments/is-author? com) [:span.isAuthor.indicator "*"])
  (if-not (comments/voted? com)
    (link-to {:class "upvote indicator"} (comments/upvote-url com) "&#9650;")))

(defpartial comment-count [{:keys [_id score] :as post}]
            (let [comment-count (fetch-count :comments :where {:post_id _id})]
              (link-to {:title score} (posts/post-url post) (str comment-count " comment" (if (not= comment-count 1) "s" "")))))

(defpartial comment-subtext [{:keys [ts author points post_id _id] :as com}]
            [:div.subtext.comment
             (upvote-comment-link com)
             [:span.points (comment-points com)]
             [:span.author (user-link author)]
             [:span.date (str (utils/time-ago ts) " ")]
             [:span.link (comment-link _id "link")]])

(defpartial comment-item [{:keys [_id body] :as com} & {:keys [indent] :or {indent 0}}]
            [:li
             (comment-subtext com)
             [:div.commentBody (string/replace body "\n" "<br />")]
             [:div.subtext.comment (comment-link _id "reply")]
             (if (:replies com)
               [:ol.replies (map #(comment-item % :indent (inc indent))
                         (:replies com))])])

(defpartial comment-list [comments]
    (if (not-empty comments)
      [:ol.commentList (map comment-item comments)]
      [:div.empty "No comments"]))

(defpartial upvote-link [post]
  (if (posts/is-author? post) [:span.isAuthor.indicator "*"])
  (if-not (posts/voted? post)
    (link-to {:class "upvote indicator"} (posts/upvote-url post) "&#9650;")))

(defpartial post-subtext [{:keys [ts author points] :as post}]
            [:div.subtext
              [:span.points (format-points points)]
              [:span.author (user-link author)]
              [:span.date (utils/time-ago ts)]
              [:span.commentCount (comment-count post)]])

(defpartial post-item [{:keys [link title author ts desc] :as post}]
  (when post
    (let [ link (if desc (posts/post-url post) link)]
      [:li.post
       [:h3.title
        (upvote-link post)
        (link-to {:class "postLink"} link title)
       [:span.domain "(" (or (extract-domain-from-url link) "Self") ")"]]
      (post-subtext post)])))

(defpartial post-list [items]
            (if (not-empty items)
              [:ol.postList (map post-item items)]
              [:div.empty "No posts"]))

(defpartial error-text [errors]
            [:span.error (string/join " " errors)])

(defpartial layout [& content]
            (html5
              [:head
               [:title "MTP News"]
               (include-css "/css/style.css")]
              [:body
               [:div#wrapper
                [:header
                 (link-to "/" [:img.logo {:src "/img/hacker-school-logo.png"}])
                 [:h1#logo
                  (link-to "/" "MTP News")]
                 [:ul
                  [:li (link-to "/newest" "new")]
                  [:li (link-to "/newcomments" "comments")]
                 #_ [:li (link-to "http://www.hackruiter.com/companies" "jobs")]
                  [:li (link-to "/submit" "submit")]]
                 (let [hs_id (users/current-user)]
                  (if hs_id
                    [:div.user.loggedin
                      [:span.username (user-link hs_id) " (" (users/get-karma hs_id) ")"]
                      (link-to "/logout" "log out")]
                    [:div.user.loggedout
                      (link-to "/register" "register") ;; Uncomment to allow registration
                      (link-to "/login" "log in")]))]
                [:div#content content]
                [:footer
                 [:ul
                  [:li (link-to "/lists" "Lists")]
                  [:li (link-to "/bookmarklet" "Bookmarklet")]
                  #_[:li (link-to "http://www.hackerschool.com" "Hacker School")]
                  [:li (link-to "https://github.com/tutysara/hsnews/issues" "Feature Requests")]
                  [:li (link-to "https://github.com/tutysara/hsnews" "Source on Github")]]]]]))
