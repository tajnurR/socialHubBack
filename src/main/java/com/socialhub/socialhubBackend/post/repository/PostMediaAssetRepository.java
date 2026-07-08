package com.socialhub.socialhubBackend.post.repository;

import com.socialhub.socialhubBackend.post.domain.PostMediaAsset;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostMediaAssetRepository extends JpaRepository<PostMediaAsset, Long> {

    List<PostMediaAsset> findByPostIdOrderByDisplayOrderAscIdAsc(Long postId);

    List<PostMediaAsset> findByPostIdInOrderByPostIdAscDisplayOrderAscIdAsc(Collection<Long> postIds);

    void deleteByPostId(Long postId);
}
