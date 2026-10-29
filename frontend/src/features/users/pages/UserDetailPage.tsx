/** 1.3.2 User Detail — SRS III.1.3.2 */
import { useParams } from 'react-router';
import { CreateUserForm } from '../components/CreateUserForm';
import { UserAccountDetail } from '../components/UserAccountDetail';

export function UserDetailPage() {
  const { userId } = useParams();
  return userId ? <UserAccountDetail key={userId} userId={userId} /> : <CreateUserForm />;
}

export default UserDetailPage;
