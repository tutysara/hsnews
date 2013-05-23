(ns hsnews.models.post
  (:use [hsnews.models.congomongo] ;; Import Redefined with-ref-fetching
        [somnium.congomongo :exclude [with-ref-fetching]]
        [hsnews.utils])
  (:require [clj-time.core :as ctime]
            [clj-time.coerce :as coerce]
            [noir.validation :as vali]
            [hsnews.models.user :as users]
            [clojure.walk :as walk]))


(def posts-per-page 30)

(defn valid? [{:keys [title link desc]}]
  (vali/rule (vali/has-value? title)
             [:title "Please enter a title"])
  (vali/rule (vali/max-length? title 255)
             [:title "Titles can be no more than 255 characters"])
  (vali/rule (not
              (and
               (vali/has-value? desc)
               (vali/has-value? link)))
               [:link "Description can be present only when there is no link"])
  (vali/rule (or (vali/has-value? link) (vali/has-value? desc))
             [:title "Please enter a link"])
  (vali/rule (or (vali/max-length? link 2048) (vali/has-value? desc))
             [:title "Links can be no more than 2048 characters"])
  (vali/rule (or (url? link) (vali/has-value? desc))
             [:link "Not a valid link format"])
  (not (vali/errors? :title :link)))

(defn prepare-new [post]
  (let [ts (ctime/now)]
    (-> post
      (assoc :ts (coerce/to-long ts))
      (assoc :author (users/current-user))
      (assoc :points 1)
      (assoc :score 1.0)
      (assoc :voters {(users/current-user) true})
      (assoc :last-updated (coerce/to-long ts)))))

(defn id->post [id]
  (fetch-by-id :posts (object-id id)))

(defn id->comment [id]
  (first
   ((with-ref-fetching #(fetch :comments :where {:_id (object-id %)} :sort {:points -1})) id)))

;Decay
(defn get-posts-to-decay []
  (let [ts (ctime/now) fivemin 300000]
    (fetch :posts :where {:last-updated {:$lte (- (coerce/to-long ts) fivemin)}})))

(defn decay-post [{:keys [score] :as post}]
  (let [multiplier 0.9 ts (ctime/now)]
    (update! :posts
             post
             {:$set {:score (* multiplier score) :last-updated (coerce/to-long ts)}})))

(defn decay! []
  (doall (map decay-post (get-posts-to-decay))))

(defn is-author? [{:keys [author]}]
  (= author (users/current-user)))

(defn add! [post]
  (when (valid? post)
    (do
      (insert! :posts (prepare-new post))
      (decay!))))

(defn voted? [{:keys [voters]}]
  (contains? voters (keyword (users/current-user))))

(defn upvote! [{:keys [author] :as post}]
  (if-not (voted? post)
    (do
      (update! :posts
               post
               {:$inc {:points 1 :score 1} :$set {(str "voters." (users/current-user)) true}})
      (update! :users (fetch-one :users :where {:hs_id author}) {:$inc {:karma 1}})
      (decay!))))

(defn get-page [page sort-options]
  (let [page-num (dec (Integer. page))
        skip (* page-num posts-per-page)]
    (fetch :posts :limit posts-per-page :skip skip :sort sort-options)))

(defn get-top []
  (get-page 1 {:score -1}))

(defn get-newest []
  (get-page 1 {:ts -1}))

(defn post-url [{:keys [_id] :as post}]
  (str "/posts/" _id))

(defn upvote-url [{:keys [_id]}]
  (str "/posts/" _id "/upvote"))

(defn get-comments [{:keys [_id]}]
  (fetch :comments :where {:post_id _id :parent_id ""} :sort {:points -1}))

(defn point-sort [coll]
  (sort-by :points > coll))

(defn sort-comments-tree [tree]
  (let [sorted-coll (map (fn [x]
                           (let [replies (:replies x)]
                             (if replies
                               (assoc x :replies (sort-comments-tree replies))
                               x)))
                         tree)]
    (point-sort sorted-coll)))

(defn get-comments-tree [post]
  (let [tree ((with-ref-fetching get-comments) post)]
    (sort-comments-tree tree)))

(defn get-by-user [hs_id]
  (fetch :posts :where {:author hs_id}))
